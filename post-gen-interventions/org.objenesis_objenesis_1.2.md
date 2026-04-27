# Post-generation intervention report

Library: org.objenesis:objenesis:1.2
Stage: `metadata_fix_failed`

## Summary

The post-generation failure is still **metadata-related**, not a test bug or unsupported platform feature.

The Codex `metadata-fix` run partially improved the lane from `17/24` passing tests to `21/24`, but the last 3 native-image failures all remained on the same unresolved reflection gap:

- `org_objenesis.objenesis.SunReflectionFactoryInstantiatorTest > createsInstancesWithoutRunningTheTargetConstructor()`
- `org_objenesis.objenesis.PercInstantiatorTest > delegatesInstanceCreationToConfiguredPercHook()`
- `org_objenesis.objenesis.PercSerializationInstantiatorTest > delegatesInstanceCreationToConfiguredPercSerializationHook()`

## Root cause by failing test

### 1. `SunReflectionFactoryInstantiatorTest.createsInstancesWithoutRunningTheTargetConstructor()`

**Root cause:** metadata-related.

The native executable still lacks effective reflection registration for reflective invocation of `java.lang.Object`'s no-arg constructor:

```json
{
  "type": "java.lang.Object",
  "methods": [
    {
      "name": "<init>",
      "parameterTypes": []
    }
  ]
}
```

The failure happens inside `org.objenesis.instantiator.sun.SunReflectionFactoryInstantiator.newInstance`, which uses the serialization-constructor path backed by `java.lang.Object()`.

### 2. `PercInstantiatorTest.delegatesInstanceCreationToConfiguredPercHook()`

**Root cause:** metadata-related.

This test fails for the same missing registration, but indirectly. Its helper method `newUninitializedPercInstantiator()` creates a `PercInstantiator` by first using `SunReflectionFactoryInstantiator`, so it hits the same missing reflective call to `java.lang.Object()` before the Perc-specific assertions can run.

### 3. `PercSerializationInstantiatorTest.delegatesInstanceCreationToConfiguredPercSerializationHook()`

**Root cause:** metadata-related.

This test also bootstraps its target instance through `SunReflectionFactoryInstantiator` in `newUninitializedPercSerializationInstantiator()`, so it fails on the same unresolved `java.lang.Object()` reflection registration before exercising the Perc serialization hook.

## Why this was not resolved by Codex

The `logs/org.objenesis:objenesis:1.2/metadata-fix/codex.log` run shows that Codex already recognized the failure as missing metadata and tried multiple metadata shapes/locations, including:

- a library metadata stub under `metadata/org.objenesis/objenesis/1.2/reachability-metadata.json`
- test metadata under `tests/src/org.objenesis/objenesis/1.2/src/test/resources/META-INF/native-image/reachability-metadata.json`
- exact-path test metadata under `tests/src/org.objenesis/objenesis/1.2/src/test/resources/META-INF/native-image/org.objenesis/objenesis/reachability-metadata.json`
- a legacy `reflect-config.json` in the same exact-path test resource directory

Despite those attempts, native-image still reported the same `MissingReflectionRegistrationError` for `public java.lang.Object()`.

So the issue is not that the failing tests are invalid; it is that the remaining required registration shape for the `SunReflectionFactoryInstantiator` path is still unresolved from the available error output. Codex could narrow the problem to the `java.lang.Object()` constructor path, but could not derive a metadata form that native-image accepted for this case.

## Action taken

No tests were removed.

Because all 3 failures are still metadata-related, removing the tests would hide a real unresolved reachability gap rather than eliminate a non-metadata failure.

## Why the remaining generated support should be preserved

The generated support should still be preserved because:

- the Codex repair materially improved coverage (`21/24` tests passing)
- the passing tests already validate meaningful `objenesis` behavior across other instantiator paths
- the remaining failures are concentrated in one unresolved `SunReflectionFactoryInstantiator` / `java.lang.Object()` metadata gap
- keeping the generated tests preserves a precise reproducer for the missing metadata instead of discarding useful coverage

In short, the remaining generated support is valuable and should stay in place; only the final `java.lang.Object()` reflection registration for the `SunReflectionFactoryInstantiator` path still needs a correct metadata solution.
