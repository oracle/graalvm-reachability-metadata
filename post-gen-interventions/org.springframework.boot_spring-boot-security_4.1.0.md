# Post-generation intervention report

Library: org.springframework.boot:spring-boot-security:4.1.0
Stage: `metadata_fix_failed`

## Summary

The native test run failed during Spring Boot auto-configuration metadata reading. All generated tests that start a `WebApplicationContextRunner` with `SecurityAutoConfiguration` / `UserDetailsServiceAutoConfiguration` reached the same root failure:

```text
java.lang.IllegalStateException: Unable to read meta-data for class org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
Caused by: java.io.FileNotFoundException: class path resource [org/springframework/boot/security/autoconfigure/UserDetailsServiceAutoConfiguration.class] cannot be opened because it does not exist
```

The requested Codex log path, `logs/org.springframework.boot:spring-boot-security:4.1.0/metadata-fix/codex.log`, was not present in this checkout. The Gradle failure output and available native test results point to incomplete reachability metadata, not to an unsupported native-image feature or a generated test bug.

## Root cause by failing test

The Gradle excerpt reports the same metadata-resource failure for the generated test class. For each affected test, Spring needs to read `.class` resources from `spring-boot-security` during auto-configuration sorting/condition evaluation, but the native image does not contain `org/springframework/boot/security/autoconfigure/UserDetailsServiceAutoConfiguration.class` as a classpath resource.

- `securityPropertiesBindDefaultUserAndCreateInMemoryUserDetailsService()` — metadata-related; Spring cannot read `UserDetailsServiceAutoConfiguration.class`.
- `generatedDefaultPasswordIsUsableWhenNoPasswordPropertyIsConfigured()` — metadata-related; same missing auto-configuration class resource.
- `customPasswordEncoderKeepsConfiguredPasswordEncoded()` — metadata-related; same missing auto-configuration class resource.
- `customUserDetailsServiceMakesDefaultInMemoryUserBackOff()` — metadata-related; same missing auto-configuration class resource.
- `servletSecurityAutoConfigurationCreatesDefaultFilterChainAndRegistration()` — metadata-related; same missing auto-configuration class resource.
- `customSecurityFilterChainMakesServletDefaultFilterChainBackOff()` — metadata-related; same missing auto-configuration class resource.
- `servletPathRequestMatchesCommonStaticResourcesAndHonorsExclusions()` — metadata-related in the Gradle excerpt; same missing auto-configuration class resource.
- `servletPathRequestMatchesSelectedStaticResourceLocations()` — metadata-related in the failing native run; same class-resource/autoconfiguration metadata issue.
- `securityFilterPropertiesExposeDefaultOrderAndDispatcherTypes()` — metadata-related in the Gradle excerpt; same missing auto-configuration class resource.
- `applicationContextRequestMatcherUsesCurrentWebApplicationContext()` — metadata-related in the failing native run; same class-resource/autoconfiguration metadata issue.

The available persisted native result also shows metadata symptoms from an earlier/adjacent run, including inactive conditional reflection metadata for `org.springframework.boot.security.autoconfigure.web.servlet.DefaultWebSecurityCondition()` and missing `InMemoryUserDetailsManager` beans after auto-configuration evaluation. Those are consequences of incomplete or incorrectly conditioned metadata, not unsupported native behavior.

## Intervention decision

No generated tests were removed.

This is a metadata failure: native-image is missing Spring Boot Security auto-configuration class resources and still has incomplete/incorrectly conditioned reflection support for servlet security condition evaluation. The tests do not depend on runtime bytecode generation, class redefinition, Java-agent attach, instrumentation, native-image substitutions, or Byte Buddy-backed mocking paths. Therefore the tests should be kept and the remaining issue should be fixed by completing the reachability metadata rather than deleting coverage.

## Why the generated support should be preserved

The generated support exercises real, supported `spring-boot-security` behavior:

- binding `SecurityProperties` and creating default/custom `UserDetailsService` beans;
- servlet security auto-configuration and default/custom `SecurityFilterChain` behavior;
- static-resource request matching through `PathRequest`;
- `SecurityFilterProperties` defaults and dispatcher type binding;
- `ApplicationContextRequestMatcher` behavior in a servlet application context.

These are meaningful native-image coverage targets for the library. Preserving them keeps a regression test for the missing `.class` resource metadata and the remaining conditional reflection metadata required by Spring Boot Security auto-configuration.
