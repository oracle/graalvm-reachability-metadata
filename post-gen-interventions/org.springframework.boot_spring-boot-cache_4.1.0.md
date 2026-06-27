# Post-generation intervention report

Library: org.springframework.boot:spring-boot-cache:4.1.0
Stage: metadata_fix_failed

## Summary

The native test failure was not a remaining reachability-metadata miss. The generated Spring application-context tests built successfully and then failed at native runtime with a GraalVM segfault while Spring parsed `CacheAutoConfiguration$CacheManagerEntityManagerFactoryDependsOnConfiguration` annotation metadata. Later Codex diagnostics also produced secondary `NoClassDefFoundError: Could not initialize class org.springframework.beans.CachedIntrospectionResults` failures during context refresh/teardown, but those were masked cleanup symptoms rather than actionable `Missing*RegistrationError` metadata reports.

## Root cause and intervention

Removed the generated tests that start a Spring `ApplicationContextRunner` or direct `AnnotationConfigApplicationContext` for cache auto-configuration:

- `simpleCacheAutoConfigurationCreatesNamedConcurrentMapCachesAndAppliesCustomizers`
- `genericCacheAutoConfigurationAdaptsUserProvidedCacheBeans`
- `noOpCacheAutoConfigurationDisablesStorageButStillReturnsCachesByName`
- `autoConfigurationBacksOffWhenUserProvidesCacheManager`
- `simpleCacheAutoConfigurationCreatesDynamicCachesWhenNamesAreNotConfigured`
- `cacheAutoConfigurationSupportsDeclarativeCacheableOperations`
- Codex's diagnostic `diagnosticStartupFailure`

These tests depend on runtime Spring configuration-class parsing and optional annotation payload resolution in native image. The observed failure was a native-image/runtime limitation/bug path, not a metadata entry that Codex could finish resolving. I also removed the now-unused `spring-boot-test` test dependency; the temporary JPA dependency added during Codex diagnostics was not preserved.

No metadata files were modified by this intervention.

## Preserved support

The remaining generated tests still exercise supported, metadata-relevant `spring-boot-cache` behavior without runtime application-context startup:

- `CacheProperties` and nested provider-specific property classes.
- `CacheManagerCustomizers` generic customizer dispatch for concrete cache managers.
- Spring Boot import-candidate discovery for `CacheAutoConfiguration`.

This preserves useful API and resource coverage for the library while removing only the native-runtime Spring context paths that caused the post-generation failure.

## Verification

Ran:

```console
./gradlew test -Pcoordinates=org.springframework.boot:spring-boot-cache:4.1.0 --stacktrace
```

with `GRAALVM_HOME`/`JAVA_HOME` set to GraalVM `25.0.3+9.1`. The task passed with 3 native tests successful and 0 failed.
