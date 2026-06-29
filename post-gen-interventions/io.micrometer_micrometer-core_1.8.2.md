# Post-generation intervention report

Library: io.micrometer:micrometer-core:1.8.2
Stage: metadata_fix_failed

## Summary

The native test executable built successfully, but `:nativeTest` failed at runtime in the generated Hazelcast coverage:

- `HazelcastIMapAdapterInnerLocalMapStatsTest.bindsMetricsFromHazelcastLocalMapStats()`
- `HazelcastIMapAdapterInnerNearCacheStatsTest.bindsMetricsFromHazelcastNearCacheStats()`
- `HazelcastIMapAdapterTest.bindsMetricsForHazelcastIMap()`

All three failures share the same root cause: `io.micrometer.core.instrument.binder.cache.HazelcastIMapAdapter` fails during static initialization while resolving Hazelcast methods with `MethodHandles.Lookup.findVirtual(...)`. The first failure reports `NoSuchMethodException` / `NoSuchMethodError` for `com.hazelcast.map.IMap.getName()`, and the later two failures are follow-on `NoClassDefFoundError` failures after the adapter class could not initialize.

## Root cause classification

This is metadata-related, not a generated-test bug or an unsupported native-image behavior category that should be removed. The failing path is Micrometer's legitimate Hazelcast cache binder resolving Hazelcast accessor methods through method handles. Codex attempted to add regular reflection metadata, but that did not satisfy the `findVirtual(...)` method-query requirement. It then attempted method-query metadata (`queryAllPublicMethods` / `queriedMethods`), which matches the failing access shape, but the repository's shipped library metadata schema rejected those fields for `metadata/io.micrometer/micrometer-core/1.8.2/reachability-metadata.json`.

No generated Hazelcast test was removed because the failures are due to missing/unrepresentable method-query metadata in the current central metadata format, rather than a test depending on bytecode generation, agent attach, class redefinition, instrumentation, substitutions, or Byte Buddy-backed mocking.

## Remaining generated support

The remaining generated support should be preserved. The non-Hazelcast tests passed in the native run and cover valid Micrometer core behavior: time-window histogram snapshots, executor-service metrics, file-descriptor metrics, processor metrics, and property validation. The Hazelcast tests also exercise real Micrometer binder behavior and should remain as evidence of the metadata gap unless the metadata format is changed or a test-only native-image metadata resource is intentionally added later to cover method-query registration.
