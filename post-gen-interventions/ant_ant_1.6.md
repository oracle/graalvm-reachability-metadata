# Post-generation intervention report

Library: ant:ant:1.6
Stage: `metadata_fix_failed`

## Summary

The requested Codex log path, `logs/ant:ant:1.6/metadata-fix/codex.log`, is not present in this worktree, so the conclusions below are based on the generated tests/config and the current Gradle/native test failure output.

The generated support was partially valid: after removing non-metadata native-image incompatibility tests, the native test suite dropped from many failures to a single remaining metadata-related failure.

## Non-metadata failures removed

I removed the generated tests below because their failures were caused by native-image limitations or test-only classpath assumptions, not by missing reachability metadata.

- `ant.ant.AntClassLoaderTest#exercisesClassInitializationAndClassLoading(Path)`
  - Root cause: the test writes raw `.class` bytes and asks `AntClassLoader` to define them at runtime.
  - Native failure: `UnsupportedFeatureError` from runtime class definition.
  - Why non-metadata: this is ahead-of-time runtime class definition, which Native Image rejects by default.

- `ant.ant.ExecuteTest#initializesFreshlyLoadedExecuteClass(Path)`
  - Root cause: the test copies `Execute` bytecode and reloads it through `AntClassLoader`.
  - Native failure: `UnsupportedFeatureError` from runtime class definition.
  - Why non-metadata: same unsupported runtime class loading pattern as above.
  - Related cleanup: removed the generated test-only `Execute` resource pattern from `src/test/resources/META-INF/native-image/ant-ant-tests/resource-config.json`.

- `ant.ant.Javac13Test#compilesSourceFileThroughModernCompilerAdapter(Path)`
  - Root cause: the test executes Ant `javac1.3` compilation inside the native test image.
  - Native failure: `ExceptionInInitializerError` / `NullPointerException` from `com.sun.tools.javac.file.Locations` during compiler startup.
  - Why non-metadata: this is a JDK compiler/native-image runtime limitation, not an Ant metadata gap.

- `ant.ant.XSLTProcessTest#transformsSingleFileWithDefaultTraxProcessor(Path)`
  - Root cause: the default JDK TraX/XSLTC processor crashes while compiling the stylesheet.
  - Native failure: `TransformerConfigurationException` wrapping an internal `NullPointerException` in XSLTC.
  - Why non-metadata: this is an internal JDK/XSLTC runtime issue. The remaining `XSLTProcessTest` coverage still exercises Ant processor resolution through custom and optional processors.

- `ant.ant.ClassConstantsTest#delegatesConstantExtractionToTheClassHelper()`
  - Root cause: the generated test depended on a test-only helper class `org.apache.tools.ant.filters.util.JavaClassHelper` that shadows the library helper on the JVM.
  - Native failure: the image loaded Ant's real BCEL-based helper from `ant-1.6.jar` instead of the test helper because of image classpath ordering, so the shadowing assumption broke.
  - Why non-metadata: this is a generated test bug / classpath-shadowing assumption, not a metadata problem in the library.
  - Related cleanup: removed the test-only helper source and its no-longer-needed reflection entry.

## Remaining metadata-related failure

I kept the following test because its failure is still metadata-related:

- `ant.ant.RegexpMatcherFactoryTest#createsDefaultJdkMatcherAfterAvailabilityProbe()`
  - Root cause: `RegexpMatcherFactory` selects `org.apache.tools.ant.util.regexp.Jdk14RegexpMatcher` through `Class.forName("org.apache.tools.ant.util.regexp.Jdk14RegexpMatcher").newInstance()` after probing `java.util.regex.Matcher`.
  - Native failure: the factory still falls through to `No supported regular expression matcher found`.
  - Missing metadata: the optional regexp implementation selected only by string is still not retained/reachable strongly enough in the native image at runtime.
  - Why Codex could not fully resolve it: this path is hidden behind legacy reflective selection in Ant's fallback factory logic, so Codex only partially addressed it. The generated reflection config already includes `org.apache.tools.ant.util.regexp.Jdk14RegexpMatcher`, but that was not sufficient to make the default matcher selection succeed in the produced image.

## Why the remaining generated support should be preserved

The remaining generated support should still be preserved because it is already providing broad, useful coverage for `ant:ant:1.6`:

- after the unsupported tests were removed, `81` of `82` native tests pass;
- the surviving suite still validates major Ant behaviors such as task loading, task definitions, RMIC adapters, XML catalog handling, selector wiring, JDBC driver loading, filter readers, mappers, macro/task behavior, and custom/optional XSLT processor resolution;
- the remaining failure is a narrow metadata gap in Ant's legacy regexp factory path, not evidence that the overall generated support is unusable.

## Files changed

- Removed failing generated tests:
  - `tests/src/ant/ant/1.6/src/test/java/ant/ant/Javac13Test.java`
  - `tests/src/ant/ant/1.6/src/test/java/ant/ant/ClassConstantsTest.java`
- Removed failing test-only helper:
  - `tests/src/ant/ant/1.6/src/test/java/org/apache/tools/ant/filters/util/JavaClassHelper.java`
- Updated remaining generated tests/config:
  - `tests/src/ant/ant/1.6/src/test/java/ant/ant/AntClassLoaderTest.java`
  - `tests/src/ant/ant/1.6/src/test/java/ant/ant/ExecuteTest.java`
  - `tests/src/ant/ant/1.6/src/test/java/ant/ant/XSLTProcessTest.java`
  - `tests/src/ant/ant/1.6/src/test/resources/META-INF/native-image/ant-ant-tests/reflect-config.json`
  - `tests/src/ant/ant/1.6/src/test/resources/META-INF/native-image/ant-ant-tests/resource-config.json`
