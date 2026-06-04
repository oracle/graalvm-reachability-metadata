Task:
Add more source-guided runtime API coverage for `{library}`.

Source Context:
{source_context_overview}

Continue the same experiment: do not use a dynamic-access report. Inspect the read-only source context for runtime API patterns, then write tests that reach those paths through public library APIs.

Prioritize source paths that use one of these API families:
- reflection: class loading, constructor/method/field lookup, reflective construction, field access, method invocation, or reflective array creation.
- resources: class or classloader resource lookup.
- serialization: object input or output stream serialization hooks.
- proxy: JDK dynamic proxy creation or proxy class lookup.

Rules:
- Do not change test logic that is already done.
- Do not re-test classes or flows already present in the file.
- Use only public library APIs from tests.
- Do not directly call reflection, serialization, proxy, resource lookup, JNI, or internal library methods from the test just to satisfy the table.
- Modify only the test file. Update build.gradle only if absolutely required.
- Keep tests version-agnostic. Do not hardcode the artifact version in normal test inputs or assertions.
- The tests must execute under native image. Do not skip, disable, or short-circuit test logic in native image using assumptions, `@DisabledInNativeImage`, `isNativeImageRuntime()`, `ImageInfo.inImageRuntimeCode()`, or equivalent guards.
- Every individual test must complete in under 60 seconds. Use bounded waits and close all clients, servers, executors, streams, and other background resources.
