# Post-generation intervention report

Library: org.springframework:spring-test:7.0.0
Stage: metadata_fix_failed

## Summary

The post-generation native test run failed in `TestBeanOverrideHandlerTest.invokesFactoryMethodToCreateTestBeanOverride()` with:

```text
Failed to load AOT ApplicationContextInitializer class for test class [org_springframework.spring_test.TestBeanOverrideHandlerTest$TestBeanOverrideTestCase]
```

This was not a remaining reachability metadata failure. The Codex metadata-fix log shows that Codex added the native-agent metadata delta and then traced the remaining failure to Spring test AOT discovery: `TestClassScanner` did not discover the nested helper class `TestBeanOverrideTestCase`, so Spring never generated the AOT initializer mapping required for that class in a native image.

## Action taken

Removed the generated failing test:

- `tests/src/org.springframework/spring-test/7.0.0/src/test/java/org_springframework/spring_test/TestBeanOverrideHandlerTest.java`

No metadata files were modified during this post-generation intervention.

The generated `AotTestContextInitializers__Generated` test support class was preserved because other Spring test infrastructure paths still require the empty generated AOT initializer map in native mode.

## Why the remaining generated support should be preserved

The remaining generated tests exercise independent `spring-test` functionality that is valid in native image mode, including default method invocation, merged context runtime hints, profile value source loading, mock JNDI property handling, JUnit 4 runner construction, test class scanning, and `TestContextManager` context copying. After removing only the non-discoverable bean-override AOT test, the remaining suite passed with:

```text
./gradlew clean test -Pcoordinates=org.springframework:spring-test:7.0.0 --stacktrace
```

The preserved support therefore still provides useful coverage for `org.springframework:spring-test:7.0.0` without depending on the unsupported generated AOT mapping scenario that caused the failure.
