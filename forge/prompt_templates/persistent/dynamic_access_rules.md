Rules:
- Add or refine tests so execution reaches uncovered dynamic-access call sites.
- Keep coverage for each dynamic-access class in its own dedicated `{test_language_display_name}` test file under `src/test/{test_source_dir_name}` in `graalvm-reachability-metadata`.
- Maintain a one-to-one mapping between dynamic-access report classes and generated test class files.
- Never use `$` in test class or file names. When naming test classes for inner or anonymous classes (classes whose name contains `$`), replace `$` followed by a number (anonymous classes like `Foo$4`) with `Anonymous4` (e.g., `FooAnonymous4Test`), and replace `$` followed by a name (named inner classes like `Foo$Bar`) with `Inner` and the name (e.g., `FooInnerBarTest`).
- Do not broaden the patch into unrelated features.
- Use upstream test sources only as behavioral examples.
- Use documentation and source context only as API guidance.
- Do not create source stubs, fake replacements, or shadow classes for library or dependency API types in their real packages. If a needed API is missing from the test classpath, add the correct test dependency or leave the call site unreached with an explanation.
- Target supported library behavior. Do not make an uncovered dynamic-access call "covered" by asserting a known bug, regression, broken path, or version-specific failure in the target artifact.
- Do not use reflection directly in the tests unless the public API requires it naturally.
- Do not compile or run tests yourself. The workflow will do that externally.
- Follow idiomatic `{test_language_display_name}` coding conventions.
- All top-level test classes must be public.
- Keep tests outside the library's packages. Do not place a test in the same package as the library just to access package-private or internal code.
- Keep tests version-agnostic. Do not hardcode the artifact version in normal test inputs or assertions.
- Exception assertions are acceptable only for documented, supported negative-path APIs. Do not write tests whose method name, comments, or assertions describe a known broken behavior path such as "fails before", "regression", "broken", or "version-specific" failure.
- Every individual test must complete in under 60 seconds. Use bounded waits and close all clients, servers, executors, and other background resources.
- When tests use connection, request, read, socket, server, client, process, database, messaging, or HTTP timeouts, set each explicit timeout to at least 10 seconds. Shorter timeouts are flaky under native-image-agent metadata generation and Native Image startup, while unbounded waits are still not allowed.
- Do not make tests depend on Native Image resource metadata for temporary, build, or
  machine-local absolute paths. If a test creates files under a temp/build directory,
  exercise them through normal file APIs or create the optional file that the library
  expects; do not rely on classloader resource lookup for paths such as `/tmp/...`,
  JUnit temp dirs, or `build/...`.
- Never generate, write, or modify reachability metadata or Native Image config entries. Do not create or edit `reachability-metadata.json`, `reflect-config.json`, `resource-config.json`, `proxy-config.json`, `serialization-config.json`, `jni-config.json`, `predefined-classes-config.json`, or any other file under `src/test/resources/META-INF/native-image`; Forge handles metadata generation and merging externally.

Native Image execution contract (non-negotiable):

Every test you create or edit must run and assert the same behavior under Native Image as on the JVM. Reviewers reject any PR whose tests skip or tolerate Native Image failures, the PR is closed, and the entire run is discarded. A test that is green only because it dodges Native Image is a failed deliverable, not a fix.

- Never skip Native Image execution: no `assumeFalse("runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode")))`, early returns, `@DisabledInNativeImage`, `isNativeImageRuntime()`, `ImageInfo.inImageRuntimeCode()`, or equivalent guards.
- Never tolerate Native Image failures: do not catch an exception or error and accept it because the code detects native-image runtime or recognizes a known native-image failure message. A failure that only happens under Native Image signals missing reachability metadata or unsupported behavior; surface it and never hide it inside the test.

Bad: test skipped under Native Image (rejected in review):
```java
assumeFalse("runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode")));
```

Bad: failure swallowed under Native Image (rejected in review):
```java
try {{
    Function<String, Integer> length = MethodInvokers.asFunction(method);
    assertThat(length.apply("commons")).isEqualTo(7);
}} catch (IllegalArgumentException e) {{
    if (!"runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {{
        throw e;
    }}
}}
```

Sole sanctioned exception: behavior that fundamentally requires open-ended dynamic class loading that Native Image cannot support (loading classes, JARs, generated bytecode, plugin implementations, or other class definitions only discovered after the native executable is built). Catch `Error`, verify it with `NativeImageSupport.isUnsupportedFeatureError(e)` from `org.graalvm.internal.tck`, and re-throw anything else:
```java
try {{
    Plugin plugin = PluginLoader.load(pluginJar, "example.Plugin");
    assertThat(plugin.name()).isEqualTo("example");
}} catch (Error e) {{
    if (!NativeImageSupport.isUnsupportedFeatureError(e)) {{
        throw e;
    }}
}}
```
Never use this pattern for ordinary reflection, resources, serialization, dynamic proxies, JNI, or missing reachability metadata; those tests must still pass under Native Image.
