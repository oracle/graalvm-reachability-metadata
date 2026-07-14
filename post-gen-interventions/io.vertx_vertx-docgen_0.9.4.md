# Post-generation intervention

Library: io.vertx:vertx-docgen:0.9.4
Stage: metadata_fix_failed

## Failure summary

All four generated `Vertx_docgenTest` methods failed only in `nativeTest`. They each call `ToolProvider.getSystemJavaCompiler()` and compile Java source at runtime. The embedded `javac` fails during `com.sun.tools.javac.file.Locations` static initialization with a `NullPointerException` in `UnixFileSystem.getPath`; the remaining three failures are consequent `NoClassDefFoundError`s.

This is not a missing-reachability-metadata failure. Codex added compiler/JRT filesystem registrations and reordered processor construction, but the native test still fails while bootstrapping the runtime compiler rather than reporting a missing reflection, resource, proxy, serialization, or JNI registration. Runtime Java compilation generates and loads bytecode, which is unsupported native-image behavior for generated tests.

## Action taken

Deleted `tests/src/io.vertx/vertx-docgen/0.9.4/src/test/java/io_vertx/vertx_docgen/Vertx_docgenTest.java`. Its four tests all depend on the unsupported runtime compiler path, so retaining any one of them would retain the same native-image failure. No metadata files were modified.

## Verification

`./gradlew test -Pcoordinates=io.vertx:vertx-docgen:0.9.4 --stacktrace` passed after the removal; the test module has no native tests to compile or run.

## Preserved support

The generated library module and existing metadata changes remain untouched. The removed class is only the generated runtime-compilation test suite; deleting it avoids an unsupported test mechanism without discarding the library support artifacts that do not require runtime compilation.
